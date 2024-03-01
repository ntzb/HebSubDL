package com.nbs.hebsubdl;

public class ImdbJson {
    private int v;
    private String q;
    private ImdbJsonArray[] d;

    static class ImdbJsonArray {
        private String l;
        private String id;
        private String qid;
        boolean isMovie;

        public String getL() {
            return l;
        }

        public void setL(String l) {
            this.l = l;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getQid() {
            return qid;
        }

        public void setQid(String qid) {
            this.qid = qid;
            if (!qid.toLowerCase().contains("tvseries") && !qid.toLowerCase().contains("movie"))
                this.qid = null;
            else
                this.isMovie = qid.contains("movie");
        }
    }

    public int getV() {
        return v;
    }

    public void setV(int v) {
        this.v = v;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public ImdbJsonArray[] getD() {
        return d;
    }

    public void setD(ImdbJsonArray[] d) {
        this.d = d;
    }
}
