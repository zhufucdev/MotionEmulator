package com.zhufucdev.motion_emulator.hook_frontend

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import org.spongycastle.asn1.ASN1ObjectIdentifier
import org.spongycastle.asn1.x500.X500Name
import org.spongycastle.asn1.x509.AlgorithmIdentifier
import org.spongycastle.asn1.x509.BasicConstraints
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo
import org.spongycastle.cert.X509v1CertificateBuilder
import org.spongycastle.cert.X509v3CertificateBuilder
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter
import org.spongycastle.jce.provider.BouncyCastleProvider
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Security
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.*

fun generateSelfSignedKeyStore(
    alias: String = "motion_emulator_key",
    password: CharArray = NanoIdUtils.randomNanoId().toCharArray()
): KeyStore {
    val bcProvider =
        if (!Security.getProviders().any { it is BouncyCastleProvider })
            BouncyCastleProvider().also { Security.addProvider(it) }
        else
            Security.getProviders().first { it is BouncyCastleProvider }

    val kpg = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }
    val factory = KeyFactory.getInstance("RSA", "SC")
    val keyPair = kpg.generateKeyPair()

    val pub = factory.getKeySpec(keyPair.public, RSAPublicKeySpec::class.java)
    val pri = factory.getKeySpec(keyPair.private, RSAPrivateKeySpec::class.java)
    val pubKey = factory.generatePublic(RSAPublicKeySpec(pub.modulus, pub.publicExponent))
    val priKey = factory.generatePrivate(RSAPrivateKeySpec(pri.modulus, pri.privateExponent))

    // generate certificates
    val startDate = Date()
    val expiryDate = Date(startDate.time + 365 * 24 * 60 * 1000L)
    val dn = X500Name("CN=Test Certificate")
    val contentSigner = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
    val certHolder =
        X509v3CertificateBuilder(
            dn,
            BigInteger.ONE,
            startDate,
            expiryDate,
            dn,
            SubjectPublicKeyInfo.getInstance(pubKey.encoded)
        ).addExtension(ASN1ObjectIdentifier("2.5.29.19"), true, BasicConstraints(true))
            .build(contentSigner)
    val cert =
        JcaX509CertificateConverter()
            .setProvider(bcProvider)
            .getCertificate(certHolder)

    return KeyStore.getInstance("BKS", "SC").apply {
        load(null)
        setKeyEntry(alias, priKey, password, arrayOf(cert))
    }
}