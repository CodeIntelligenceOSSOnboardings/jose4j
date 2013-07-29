package org.jose4j.jwe;

import junit.framework.TestCase;
import org.jose4j.base64url.Base64Url;
import org.jose4j.lang.ByteUtil;
import org.jose4j.lang.JoseException;
import org.jose4j.lang.StringUtil;

/**
 */
public class Aes128CbcHmacSha256JweContentEncryptionAlgorithmTest extends TestCase
{
    public void testExampleEncryptFromJweAppendix2() throws JoseException
    {
        // http://tools.ietf.org/html/draft-ietf-jose-json-web-encryption-13#appendix-A.2
        String plainTextText = "Live long and prosper.";
        byte[] plainText = StringUtil.getBytesUtf8(plainTextText);

        String encodedHeader = "eyJhbGciOiJSU0ExXzUiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0";
        byte[] aad = StringUtil.getBytesAscii(encodedHeader);

        int[] ints = {4, 211, 31, 197, 84, 157, 252, 254, 11, 100, 157, 250, 63, 170, 106, 206, 107, 124, 212, 45, 111, 107, 9, 219, 200, 177, 0, 240, 143, 156, 44, 207};
        byte[] contentEncryptionKeyBytes = ByteUtil.convertUnsignedToSignedTwosComp(ints);

        byte[] iv = ByteUtil.convertUnsignedToSignedTwosComp(new int[]{3, 22, 60, 12, 43, 67, 104, 105, 108, 108, 105, 99, 111, 116, 104, 101});

        Aes128CbcHmacSha256JweContentEncryptionAlgorithm jweContentEncryptionAlg = new Aes128CbcHmacSha256JweContentEncryptionAlgorithm();
        JsonWebEncryptionContentEncryptionAlgorithm.EncryptionResult encryptionResult = jweContentEncryptionAlg.encrypt(plainText, aad, contentEncryptionKeyBytes, iv);

        Base64Url base64Url = new Base64Url();

        byte[] ciphertext = encryptionResult.getCiphertext();
        String encodedJweCiphertext = "KDlTtXchhZTGufMYmOYGS4HffxPSUrfmqCHXaI9wOGY";
        assertEquals(encodedJweCiphertext, base64Url.base64UrlEncode(ciphertext));

        byte[] authenticationTag = encryptionResult.getAuthenticationTag();
        String encodedAuthenticationTag = "9hH0vgRfYgPnAHOd8stkvw";
        assertEquals(encodedAuthenticationTag, base64Url.base64UrlEncode(authenticationTag));
    }

    public void testExampleDecryptFromJweAppendix2() throws JoseException
    {
        int[] ints = {4, 211, 31, 197, 84, 157, 252, 254, 11, 100, 157, 250, 63, 170, 106, 206, 107, 124, 212, 45, 111, 107, 9, 219, 200, 177, 0, 240, 143, 156, 44, 207};
        byte[] contentEncryptionKeyBytes = ByteUtil.convertUnsignedToSignedTwosComp(ints);

        Base64Url b = new Base64Url();

        byte[] header = StringUtil.getBytesUtf8("eyJhbGciOiJSU0ExXzUiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0");
        byte[] iv = b.base64UrlDecode("AxY8DCtDaGlsbGljb3RoZQ");
        byte[] ciphertext = b.base64UrlDecode("KDlTtXchhZTGufMYmOYGS4HffxPSUrfmqCHXaI9wOGY");
        byte[] tag = b.base64UrlDecode("9hH0vgRfYgPnAHOd8stkvw");

        Aes128CbcHmacSha256JweContentEncryptionAlgorithm jweContentEncryptionAlg = new Aes128CbcHmacSha256JweContentEncryptionAlgorithm();
        byte[] plaintextBytes = jweContentEncryptionAlg.decrypt(ciphertext, iv, header, tag, contentEncryptionKeyBytes);
        assertEquals("Live long and prosper.", StringUtil.newStringUtf8(plaintextBytes));
    }

    public void testRoundTrip() throws JoseException
    {
        String text = "I'm writing this test on a flight to Zurich";
        byte[] aad = StringUtil.getBytesUtf8("eyJhbGciOiJSU0ExXzUiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0");
        byte[] plaintext = StringUtil.getBytesUtf8(text);
        Aes128CbcHmacSha256JweContentEncryptionAlgorithm jweContentEncryptionAlg = new Aes128CbcHmacSha256JweContentEncryptionAlgorithm();
        byte[] key = ByteUtil.randomBytes(jweContentEncryptionAlg.getKeySize());
        JsonWebEncryptionContentEncryptionAlgorithm.EncryptionResult encrypt = jweContentEncryptionAlg.encrypt(plaintext, aad, key);

        byte[] decrypt = jweContentEncryptionAlg.decrypt(encrypt.getCiphertext(), encrypt.getIv(), aad, encrypt.getAuthenticationTag(), key);
        assertEquals(text, StringUtil.newStringUtf8(decrypt));
    }


}