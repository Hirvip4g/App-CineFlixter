package com.cineflixter.app;

import android.util.Log;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsUnpacker {

    private final String packedJS;

    public JsUnpacker(String packedJS) {
        this.packedJS = packedJS;
    }

    public static String unpack(String packedJS) {
        JsUnpacker unpacker = new JsUnpacker(packedJS);
        return unpacker.unpack();
    }

    public String unpack() {
        try {
            Pattern p = Pattern.compile("\\}\\s*\\('(.*)',\\s*([0-9]+|\\w+),\\s*(\\d+),\\s*'(.*?)'\\.split\\('\\|'\\)", Pattern.DOTALL);
            Matcher m = p.matcher(packedJS);

            if (m.find() && m.groupCount() >= 4) {
                String payload = m.group(1).replace("\\'", "'");
                String radixStr = m.group(2);
                String countStr = m.group(3);
                String[] symtab = m.group(4).split("\\|");

                int radix;
                try {
                    radix = Integer.parseInt(radixStr);
                } catch (NumberFormatException e) {
                    // It could be a word-based number, like 'sixteen' which is not supported in this Java version.
                    // A simple base-36 parser might work for some cases.
                    radix = 36;
                }

                int count = Integer.parseInt(countStr);

                if (symtab.length != count) {
                     // The original Java implementation's logic might work better for some cases.
                     return legacyUnpack(packedJS);
                }

                Unbase unbase = new Unbase(radix);
                Pattern wordPattern = Pattern.compile("\\b\\w+\\b");
                Matcher wordMatcher = wordPattern.matcher(payload);

                StringBuilder decoded = new StringBuilder();
                int lastIndex = 0;

                while (wordMatcher.find()) {
                    decoded.append(payload, lastIndex, wordMatcher.start());
                    String word = wordMatcher.group(0);
                    
                    int x = -1;
                    try {
                        x = unbase.unbase(word);
                    } catch (Exception e) {
                        // ignore if word is not a valid number in the given base
                    }
                    
                    String value = null;
                    if (x != -1 && x < symtab.length) {
                        value = symtab[x];
                    }

                    if (value != null && !value.isEmpty()) {
                        decoded.append(value);
                    } else {
                        decoded.append(word);
                    }
                    lastIndex = wordMatcher.end();
                }
                decoded.append(payload.substring(lastIndex));
                return decoded.toString();
            } else {
                 return legacyUnpack(packedJS);
            }
        } catch (Exception e) {
            Log.e("JsUnpacker", "Unpack failed", e);
            return legacyUnpack(packedJS); // fallback to old method
        }
    }

    private static String legacyUnpack(String packedJS) {
        try {
            Pattern pattern = Pattern.compile("eval\\(function\\(p,a,c,k,e,d\\).*?}\\('(.*?)',(\\d+),(\\d+),'(.*?)'\\.split\\('\\|'\\)");
            Matcher matcher = pattern.matcher(packedJS);

            if (!matcher.find()) {
                return null;
            }

            String payload = matcher.group(1).replace("\\'", "'");
            int a = Integer.parseInt(matcher.group(2));
            int c = Integer.parseInt(matcher.group(3));
            String[] k = matcher.group(4).split("\\|");

            if (c <= 0) return null;

            String[] symbolTable = new String[c];
            for (int i = c - 1; i >= 0; i--) {
                if (k.length > i && k[i] != null && !k[i].isEmpty()) {
                    symbolTable[i] = k[i];
                } else {
                    symbolTable[i] = baseN(i, a);
                }
            }

            StringBuilder unpacked = new StringBuilder();
            Pattern p = Pattern.compile("\\b\\w+\\b");
            Matcher m = p.matcher(payload);

            int lastIndex = 0;
            while(m.find()) {
                unpacked.append(payload, lastIndex, m.start());
                String word = m.group(0);
                try {
                    int index = fromBaseN(word, a);
                    if (index < symbolTable.length && symbolTable[index] != null) {
                        unpacked.append(symbolTable[index]);
                    } else {
                        unpacked.append(word);
                    }
                } catch(NumberFormatException e) {
                    unpacked.append(word);
                }
                lastIndex = m.end();
            }
            unpacked.append(payload.substring(lastIndex));

            return unpacked.toString();
        } catch (Exception e) {
            Log.e("JsUnpacker", "Legacy unpack failed", e);
            return null;
        }
    }

    private static String baseN(int num, int base) {
        return Integer.toString(num, base);
    }
    
    private static int fromBaseN(String str, int base) {
        return Integer.parseInt(str, base);
    }

    private static class Unbase {
        private final int radix;
        private final String ALPHABET_62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        private final String ALPHABET_95 = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
        private String alphabet = null;
        private final HashMap<Character, Integer> dictionary = new HashMap<>();

        public Unbase(int radix) {
            this.radix = radix;
            if (radix > 36) {
                if (radix < 62) {
                    alphabet = ALPHABET_62.substring(0, radix);
                } else if (radix == 62) {
                    alphabet = ALPHABET_62;
                } else if (radix > 62 && radix < 95) {
                    alphabet = ALPHABET_95.substring(0, radix);
                } else if (radix == 95) {
                    alphabet = ALPHABET_95;
                }

                if (alphabet != null) {
                    for (int i = 0; i < alphabet.length(); i++) {
                        dictionary.put(alphabet.charAt(i), i);
                    }
                }
            }
        }

        public int unbase(String str) {
            if (alphabet == null) {
                try {
                    return Integer.parseInt(str, radix);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }

            int ret = 0;
            String reversedStr = new StringBuilder(str).reverse().toString();
            for (int i = 0; i < reversedStr.length(); i++) {
                char c = reversedStr.charAt(i);
                if(dictionary.containsKey(c)){
                     ret += (int) (Math.pow(radix, i) * dictionary.get(c));
                } else {
                    return -1; // Character not in alphabet
                }
            }
            return ret;
        }
    }
}