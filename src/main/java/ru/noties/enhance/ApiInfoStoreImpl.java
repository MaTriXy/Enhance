package ru.noties.enhance;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

class ApiInfoStoreImpl extends ApiInfoStore {

    private static class TypeVersion extends ApiInfo {

        private final Map<String, ApiInfo> fields = new HashMap<>(3);
        private final Map<String, ApiInfo> methods = new HashMap<>(3);

        private TypeVersion(ApiVersion since, ApiVersion deprecated) {
            super(since, deprecated);
        }
    }

    private final Map<String, TypeVersion> map;

    ApiInfoStoreImpl(@Nonnull File apiVersions) {
        this.map = new Parser(apiVersions).parse();
    }

    @Nullable
    @Override
    public ApiInfo type(@Nonnull String type) {
        return map.get(type);
    }

    @Nullable
    @Override
    public ApiInfo field(@Nonnull String type, @Nonnull String name) {
        final TypeVersion version = map.get(type);
        return version != null
                ? version.fields.get(name)
                : null;
    }

    @Nullable
    @Override
    public ApiInfo method(@Nonnull String type, @Nonnull String signature) {
        final TypeVersion version = map.get(type);
        return version != null
                ? version.methods.get(signature)
                : null;
    }

    private static class Parser {

        private static final String NAME = "name";
        private static final String SINCE = "since";
        private static final String DEPRECATED = "deprecated";

        private final File file;

        private Parser(@Nonnull File file) {
            this.file = file;
        }

        @Nonnull
        Map<String, TypeVersion> parse() {

            final Map<String, TypeVersion> map = new HashMap<>();

            final NodeList list = classes();
            Node node;
            Element element;
            TypeVersion version;

            for (int i = 0, length = list.getLength(); i < length; i++) {

                node = list.item(i);

                if (Node.ELEMENT_NODE == node.getNodeType()) {

                    element = (Element) node;

                    version = new TypeVersion(
                            apiVersion(element.getAttribute(SINCE)),
                            apiVersion(element.getAttribute(DEPRECATED))
                    );

                    fields(version, element);
                    methods(version, element);

                    if (!isEmpty(version)) {
                        map.put(element.getAttribute(NAME), version);
                    }
                }
            }

            return map;
        }

        @Nonnull
        private NodeList classes() {
            try {

                final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                final Document document = builder.parse(file);
                document.getDocumentElement().normalize();
                return document.getElementsByTagName("class");
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        private static void fields(@Nonnull TypeVersion version, @Nonnull Element parent) {

            final NodeList list = parent.getElementsByTagName("field");

            Node node;
            Element element;
            ApiInfo apiInfo;

            for (int i = 0, length = list.getLength(); i < length; i++) {
                node = list.item(i);
                if (Node.ELEMENT_NODE == node.getNodeType()) {
                    element = (Element) node;
                    apiInfo = apiInfo(element);
                    if (apiInfo != null) {
                        version.fields.put(element.getAttribute(NAME), apiInfo);
                    }
                }
            }
        }

        private static void methods(@Nonnull TypeVersion version, @Nonnull Element parent) {

            final NodeList list = parent.getElementsByTagName("method");

            Node node;
            Element element;
            ApiInfo apiInfo;

            for (int i = 0, length = list.getLength(); i < length; i++) {
                node = list.item(i);
                if (Node.ELEMENT_NODE == node.getNodeType()) {
                    element = (Element) node;
                    apiInfo = apiInfo(element);
                    if (apiInfo != null) {
                        version.methods.put(normalizeMethodSignature(element.getAttribute(NAME)), apiInfo);
                    }
                }
            }
        }

        private static boolean isEmpty(@Nonnull TypeVersion version) {
            return version.since == null
                    && version.deprecated == null
                    && version.fields.size() == 0
                    && version.methods.size() == 0;
        }

        @Nullable
        private static ApiInfo apiInfo(@Nonnull Element element) {

            final ApiInfo apiInfo;

            final ApiVersion since = apiVersion(element.getAttribute(SINCE));
            final ApiVersion deprecated = apiVersion(element.getAttribute(DEPRECATED));

            if (since == null
                    && deprecated == null) {
                apiInfo = null;
            } else {
                apiInfo = new ApiInfo(since, deprecated);
            }

            return apiInfo;
        }

        @Nullable
        private static ApiVersion apiVersion(@Nullable String value) {
            final ApiVersion version;
            if (value == null
                    || value.length() == 0) {
                version = null;
            } else {
                int sdk;
                try {
                    sdk = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    sdk = 0;
                }
                version = ApiVersion.of(sdk);
            }
            return version;
        }

        @Nonnull
        private static String normalizeMethodSignature(@Nonnull String name) {

            // we will cut off all package info from reference types (and possibly parent class)
            // LBuilder; instead of Landroid/app/AlertDialog$Builder; so we do not have to resolve types in source code..

            final String out;

            int index = name.indexOf(';');
            if (index < 0) {
                out = name;
            } else {

                final String[] split = name.split(";");
                final StringBuilder builder = new StringBuilder(name.length());

                char c;

                int from = -1;
                int to = -1;

                for (String s : split) {

                    for (int i = 0, length = s.length(); i < length; i++) {

                        c = s.charAt(i);

                        if (from == -1 && 'L' == c) {
                            from = i;
                        } else if (c == '$' || c == '/') {
                            to = i;
                        }
                    }

                    // if both are -1 -> just append the whole thing
                    if (from == -1
                            && to == -1) {
                        builder.append(s); // should happen only at the end (if return value is not a reference type
                    } else if (from == -1 || to == -1) {
                        throw new IllegalStateException("Unexpected state for method signature part: " + s + ", whole: " + name);
                    } else {
                        builder.append(s, 0, from + 1);
                        builder.append(s, to + 1, s.length());
                        builder.append(';');
                    }

                    from = -1;
                    to = -1;
                }

                out = builder.toString();
            }

            return out;
        }
    }
}
