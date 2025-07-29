package com.cineflixter.app;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

public class JsoupConverterFactory extends Converter.Factory {
    public static JsoupConverterFactory create() {
        return new JsoupConverterFactory();
    }

    @Override
    public Converter<ResponseBody, Document> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        return new Converter<ResponseBody, Document>() {
            @Override
            public Document convert(ResponseBody value) throws IOException {
                return Jsoup.parse(value.string());
            }
        };
    }
}

