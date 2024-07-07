package com.eclectics.chamapayments.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPublicKey;

/**
 * @author Alex Maina
 * @createdOn 20/04/2022
 **/
@Slf4j
@Configuration
public class SecretKeyDecrypter {

    @Value("${app.security.jwt.keystore-name}")
    private String keyStoreName;

    @Value("${app.security.jwt.keystore-password}")
    private String keyStorePassword;

    @Value("${app.security.jwt.key-alias}")
    private String keyAlias;


    @Bean
    public KeyStore keyStore() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            ClassPathResource resource = new ClassPathResource(keyStoreName);
            keyStore.load(resource.getInputStream(), keyStorePassword.toCharArray());
            return keyStore;
        } catch (IOException | CertificateException | NoSuchAlgorithmException | KeyStoreException e) {
            log.error("UNABLE TO LOAD KEYSTORE: {}", keyStoreName, e);
        }

        throw new IllegalArgumentException("UNABLE TO LOAD KEYSTORE");
    }

    @Bean
    public RSAPublicKey jwtValidationKey(KeyStore keyStore) {
        try {
            Certificate certificate = keyStore.getCertificate(keyAlias);
            PublicKey publicKey = certificate.getPublicKey();

            if (publicKey instanceof RSAPublicKey) {
                return (RSAPublicKey) publicKey;
            }
        } catch (KeyStoreException e) {
            log.error("UNABLE TO LOAD PRIVATE KEY FROM KEYSTORE: {}", keyStoreName, e);
        }

        throw new IllegalArgumentException("UNABLE TO LOAD RSA PUBLIC KEY");
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(RSAPublicKey rsaPublicKey) {
        return NimbusReactiveJwtDecoder.withPublicKey(rsaPublicKey).build();
    }

}
