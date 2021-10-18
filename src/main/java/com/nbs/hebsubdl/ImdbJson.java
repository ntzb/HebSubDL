package com.nbs.hebsubdl;

public class ImdbJson {
    private int v;
    private String q;
    private ImdbJsonArray[] d;
    static class ImdbJsonArray {
        private String l;
        private String id;

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
