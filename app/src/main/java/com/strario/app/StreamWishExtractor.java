package com.cineflixter.app;

import com.cineflixter.app.Video;
import org.jsoup.nodes.Document;
import retrofit2.Retrofit;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Url;

public class StreamWishExtractor {

    public String getName() {
        return "Streamwish";
    }

    public String getMainUrl() {
        return "https://streamwish.to";
    }

    public String[] getAliasUrls() {
        return new String[]{
            "https://streamwish.com",
            "https://streamwish.to",
            "https://ajmidyad.sbs",
            "https://khadhnayad.sbs",
            "https://yadmalik.sbs",
            "https://hayaatieadhab.sbs",
            "https://kharabnahs.sbs",
            "https://atabkhha.sbs",
            "https://atabknha.sbs",
            "https://atabknhk.sbs",
            "https://atabknhs.sbs",
            "https://abkrzkr.sbs",
            "https://abkrzkz.sbs",
            "https://wishembed.pro",
            "https://mwish.pro",
            "https://strmwis.xyz",
            "https://awish.pro",
            "https://dwish.pro",
            "https://vidmoviesb.xyz",
            "https://embedwish.com",
            "https://cilootv.store",
            "https://uqloads.xyz",
            "https://tuktukcinema.store",
            "https://doodporn.xyz",
            "https://ankrzkz.sbs",
            "https://volvovideo.top",
            "https://streamwish.site",
            "https://wishfast.top",
            "https://ankrznm.sbs",
            "https://sfastwish.com",
            "https://eghjrutf.sbs",
            "https://eghzrutw.sbs",
            "https://playembed.online",
            "https://egsyxurh.sbs",
            "https://egtpgrvh.sbs",
            "https://flaswish.com",
            "https://obeywish.com",
            "https://cdnwish.com",
            "https://javsw.me",
            "https://cinemathek.online",
            "https://trgsfjll.sbs",
            "https://fsdcmo.sbs",
            "https://anime4low.sbs",
            "https://mohahhda.site",
            "https://ma2d.store",
            "https://dancima.shop",
            "https://swhoi.com",
            "https://gsfqzmqu.sbs",
            "https://jodwish.com",
            "https://swdyu.com",
            "https://strwish.com",
            "https://asnwish.com",
            "https://wishonly.site",
            "https://playerwish.com",
            "https://katomen.store",
            "https://hlswish.com",
            "https://streamwish.fun",
            "https://swishsrv.com",
            "https://iplayerhls.com",
            "https://hlsflast.com",
            "https://4yftwvrdz7.sbs",
            "https://ghbrisk.com",
            "https://eb8gfmjn71.sbs",
            "https://cybervynx.com",
            "https://edbrdl7pab.sbs",
            "https://stbhg.click",
            "https://dhcplay.com",
            "https://gradehgplus.com",
            "https://ultpreplayer.com"
        };
    }

    protected String referer = "";

    public Video extract(String link) throws Exception {
        Service service = Service.build(getMainUrl());

        Document document = service.get(link, referer);

        String script = java.util.regex.Pattern
            .compile("<script .*>(eval.*?)</script>", java.util.regex.Pattern.DOTALL)
            .matcher(document.toString())
            .results()
            .map(m -> m.group(1))
            .findFirst()
            .map(s -> new JsUnpacker(s).unpack())
            .orElseThrow(() -> new Exception("Can't retrieve script"));

        String source = java.util.regex.Pattern
            .compile("\"hls(\\d+)\"\\s*:\\s*\"(https:[^\"]+\\.m3u8[^\"]*)\"")
            .matcher(script)
            .results()
            .map(m -> new java.util.AbstractMap.SimpleEntry<>(
                Integer.parseInt(m.group(1)), m.group(2)))
            .sorted(java.util.Map.Entry.comparingByKey())
            .map(java.util.Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> new Exception("Can't retrieve m3u8"));

        java.util.List<Video.Subtitle> subtitles = java.util.regex.Pattern
            .compile("file:\\s*\"(.*?)\"(?:,label:\\s*\"(.*?)\")?,kind:\\s*\"(.*?)\"")
            .matcher(
                java.util.regex.Pattern
                    .compile("tracks:\\s*\\[(.*?)]")
                    .matcher(script)
                    .results()
                    .map(m -> m.group(1))
                    .findFirst()
                    .orElse("")
            )
            .results()
            .filter(m -> "captions".equals(m.group(3)))
            .map(m -> new Video.Subtitle(m.group(2), m.group(1)))
            .toList();

        Video video = new Video(
            source,
            subtitles,
            java.util.Map.of(
                "Referer", referer,
                "Origin", getMainUrl(),
                "User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:139.0) Gecko/20100101 Firefox/139.0",
                "Accept", "*/*",
                "Accept-Language", "en-US,en;q=0.5",
                "Connection", "keep-alive"
            )
        );

        return video;
    }

    public static class UqloadsXyz extends StreamWishExtractor {
        @Override
        public String getName() {
            return "Uqloads";
        }
        @Override
        public String getMainUrl() {
            return "https://uqloads.xyz";
        }

        public Video extract(String link, String referer) throws Exception {
            this.referer = referer;
            return extract(link);
        }
    }

    public static class SwiftPlayersExtractor extends StreamWishExtractor {
        @Override
        public String getName() {
            return "SwiftPlayer";
        }
        @Override
        public String getMainUrl() {
            return "https://swiftplayers.com/";
        }
    }

    public static class SwishExtractor extends StreamWishExtractor {
        @Override
        public String getName() {
            return "Swish";
        }
        @Override
        public String getMainUrl() {
            return "https://swishsrv.com/";
        }
    }

    public static class HlswishExtractor extends StreamWishExtractor {
        @Override
        public String getName() {
            return "Hlswish";
        }
        @Override
        public String getMainUrl() {
            return "https://hlswish.com/";
        }
    }

    public static class PlayerwishExtractor extends StreamWishExtractor {
        @Override
        public String getName() {
            return "Playerwish";
        }
        @Override
        public String getMainUrl() {
            return "https://playerwish.com/";
        }
    }

    public interface Service {

        static Service build(String baseUrl) {
            Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(JsoupConverterFactory.create())
                .build();

            return retrofit.create(Service.class);
        }

        @GET
        Document get(
            @Url String url,
            @Header("referer") String referer
        );
    }
}