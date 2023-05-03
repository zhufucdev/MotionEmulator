package com.zhufucdev.motion_emulator.hook_frontend

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import org.spongycastle.asn1.DERSequence
import org.spongycastle.asn1.oiw.OIWObjectIdentifiers
import org.spongycastle.asn1.x500.X500Name
import org.spongycastle.asn1.x509.AlgorithmIdentifier
import org.spongycastle.asn1.x509.BasicConstraints
import org.spongycastle.asn1.x509.Extension
import org.spongycastle.asn1.x509.GeneralName
import org.spongycastle.asn1.x509.GeneralNames
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo
import org.spongycastle.cert.X509ExtensionUtils
import org.spongycastle.cert.X509v3CertificateBuilder
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter
import org.spongycastle.jce.provider.BouncyCastleProvider
import org.spongycastle.operator.bc.BcDigestCalculatorProvider
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Security
import java.util.*

fun generateSelfSignedKeyStore(
    alias: String = "motion_emulator_key",
    password: CharArray = NanoIdUtils.randomNanoId().toCharArray(),
    san: List<GeneralName> = listOf(GeneralName(GeneralName.dNSName, "localhost"))
): KeyStore {
    val bcProvider =
        if (!Security.getProviders().any { it is BouncyCastleProvider })
            BouncyCastleProvider().also { Security.addProvider(it) }
        else
            Security.getProviders().first { it is BouncyCastleProvider }

    val kpg = KeyPairGenerator.getInstance("RSA", "SC").apply {
        initialize(2048)
    }
    val keyPair = kpg.generateKeyPair()
    val pubKey = keyPair.public
    val priKey = keyPair.private

    // generate certificates
    val startDate = Date()
    val expiryDate = Date(startDate.time + 365 * 24 * 60 * 1000L)
    val dn = X500Name("CN=Whoever")
    val pubKeyInfo = SubjectPublicKeyInfo.getInstance(pubKey.encoded)
    val digCalc = BcDigestCalculatorProvider().get(AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1))
    val extUtils = X509ExtensionUtils(digCalc)
    val contentSigner = JcaContentSignerBuilder("SHA256withRSAEncryption").build(keyPair.private)
    val certHolder =
        X509v3CertificateBuilder(dn, BigInteger(64, Random()), startDate, expiryDate, dn, pubKeyInfo)
            .addExtension(Extension.basicConstraints, true, BasicConstraints(true))
            .addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(pubKeyInfo))
            .addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(pubKeyInfo))
            .addExtension(
                Extension.subjectAlternativeName,
                false,
                GeneralNames.getInstance(DERSequence(san.toTypedArray()))
            )
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