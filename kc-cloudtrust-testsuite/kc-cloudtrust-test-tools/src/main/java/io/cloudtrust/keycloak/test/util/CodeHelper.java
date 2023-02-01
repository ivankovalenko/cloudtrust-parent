package io.cloudtrust.keycloak.test.util;

public class CodeHelper {
    private CodeHelper() {
    }

    public static String wrongCode(String validCode) {
        StringBuilder res = new StringBuilder();
        for(char c : validCode.toCharArray()) {
            if (c>='0' && c<='9') {
                c = (char)('0'+'9'-c);
            } else if (c>='a' && c<='z') {
                c = (char)('a'+'z'-c);
            } else if (c>='A' && c<='Z') {
                c = (char)('A'+'Z'-c);
            }
            res.append(c);
        }
        return res.toString();
    }
}
