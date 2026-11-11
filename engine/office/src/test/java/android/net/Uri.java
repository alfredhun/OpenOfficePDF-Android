package android.net;

public abstract class Uri {
    public static Uri parse(String uriString) {
        return new StringUri(uriString);
    }

    public static final Uri EMPTY = new StringUri("");

    public abstract String toString();

    public String getScheme() {
        String s = toString();
        int idx = s.indexOf(':');
        return idx != -1 ? s.substring(0, idx) : null;
    }

    public String getPath() {
        return toString();
    }

    private static class StringUri extends Uri {
        private final String uriString;

        StringUri(String uriString) {
            this.uriString = uriString != null ? uriString : "";
        }

        @Override
        public String toString() {
            return uriString;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Uri) && toString().equals(o.toString());
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }
    }
}
