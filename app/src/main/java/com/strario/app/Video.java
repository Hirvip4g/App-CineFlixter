package com.cineflixter.app;

import java.util.List;
import java.util.Map;

public class Video {
    private final String source;
    private final List<Subtitle> subtitles;
    private final Map<String, String> headers;

    public Video(String source, List<Subtitle> subtitles, Map<String, String> headers) {
        this.source = source;
        this.subtitles = subtitles;
        this.headers = headers;
    }

    public String getSource() {
        return source;
    }

    public List<Subtitle> getSubtitles() {
        return subtitles;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public static class Subtitle {
        private final String label;
        private final String file;

        public Subtitle(String label, String file) {
            this.label = label;
            this.file = file;
        }

        public String getLabel() {
            return label;
        }

        public String getFile() {
            return file;
        }
    }
}

