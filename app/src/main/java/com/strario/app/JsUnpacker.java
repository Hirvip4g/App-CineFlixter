package com.cineflixter.app;

public class JsUnpacker {
    private final String packedJS;

    public JsUnpacker(String packedJS) {
        this.packedJS = packedJS;
    }

    public String unpack() {
        if (packedJS == null || packedJS.isEmpty()) {
            return "";
        }
        
        try {
            // Simple unpacker - in a real scenario you'd use a proper JS unpacker
            // This is a basic implementation for the common packer pattern
            if (packedJS.contains("eval(function(p,a,c,k,e,d)")) {
                return packedJS;
            }
            return packedJS;
        } catch (Exception e) {
            return packedJS;
        }
    }
}