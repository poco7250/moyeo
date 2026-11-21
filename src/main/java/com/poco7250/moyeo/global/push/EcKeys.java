package com.poco7250.moyeo.global.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * 웹 푸시에서 쓰는 P-256 키와 바이트 표현(압축 안 한 점 65바이트, 개인키 32바이트) 사이를 바꾼다.
 * 웹 푸시 규격(RFC 8291·8292)은 키를 이 raw 형식의 base64url로 주고받는다.
 */
public final class EcKeys {

	/** 압축 안 한 P-256 공개키 길이(0x04 + X 32바이트 + Y 32바이트) */
	public static final int PUBLIC_KEY_LENGTH = 65;

	private static final int COORDINATE_LENGTH = 32;
	private static final byte UNCOMPRESSED = 0x04;
	private static final ECParameterSpec P256 = p256();

	private EcKeys() {
	}

	/**
	 * raw 공개키 65바이트로 공개키 객체를 만든다.
	 *
	 * @throws IllegalArgumentException 길이·형식이 틀리거나 곡선 위의 점이 아니면
	 */
	public static ECPublicKey publicKey(byte[] raw) {
		if (raw.length != PUBLIC_KEY_LENGTH || raw[0] != UNCOMPRESSED) {
			throw new IllegalArgumentException("압축 안 한 P-256 공개키(65바이트)가 아닙니다.");
		}
		BigInteger x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 1 + COORDINATE_LENGTH));
		BigInteger y = new BigInteger(1, Arrays.copyOfRange(raw, 1 + COORDINATE_LENGTH, PUBLIC_KEY_LENGTH));
		try {
			return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException("P-256 공개키로 읽을 수 없습니다.", e);
		}
	}

	/**
	 * raw 개인키 32바이트로 개인키 객체를 만든다.
	 *
	 * @throws IllegalArgumentException 길이가 32바이트가 아니면
	 */
	public static ECPrivateKey privateKey(byte[] raw) {
		if (raw.length != COORDINATE_LENGTH) {
			throw new IllegalArgumentException("P-256 개인키(32바이트)가 아닙니다.");
		}
		try {
			return (ECPrivateKey) KeyFactory.getInstance("EC")
					.generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), P256));
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException("P-256 개인키로 읽을 수 없습니다.", e);
		}
	}

	/**
	 * 공개키를 raw 65바이트(0x04 || X || Y)로 바꾼다.
	 */
	public static byte[] encode(ECPublicKey key) {
		byte[] result = new byte[PUBLIC_KEY_LENGTH];
		result[0] = UNCOMPRESSED;
		copyCoordinate(key.getW().getAffineX(), result, 1);
		copyCoordinate(key.getW().getAffineY(), result, 1 + COORDINATE_LENGTH);
		return result;
	}

	/**
	 * P-256 곡선 파라미터. 키 쌍 생성에도 쓴다.
	 */
	public static ECParameterSpec parameters() {
		return P256;
	}

	/**
	 * base64url(패딩 유무 상관없음)을 바이트로 바꾼다.
	 *
	 * @throws IllegalArgumentException base64url이 아니면
	 */
	public static byte[] decodeBase64Url(String value) {
		return Base64.getUrlDecoder().decode(value);
	}

	/**
	 * 바이트를 패딩 없는 base64url로 바꾼다.
	 */
	public static String encodeBase64Url(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	/**
	 * 좌표를 부호 없는 32바이트 빅엔디언으로 채운다(앞자리 0은 패딩, BigInteger 부호 바이트는 버림).
	 */
	private static void copyCoordinate(BigInteger value, byte[] target, int offset) {
		byte[] bytes = value.toByteArray();
		int length = Math.min(bytes.length, COORDINATE_LENGTH);
		System.arraycopy(bytes, bytes.length - length, target, offset + COORDINATE_LENGTH - length, length);
	}

	private static ECParameterSpec p256() {
		try {
			AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
			parameters.init(new ECGenParameterSpec("secp256r1"));
			return parameters.getParameterSpec(ECParameterSpec.class);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("JDK가 P-256 곡선을 지원하지 않습니다.", e);
		}
	}
}
