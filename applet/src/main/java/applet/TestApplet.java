package applet;

import javacard.framework.*;
import javacard.security.*;


public class TestApplet extends Applet {

    // ---- Instruction bytes ------------------------------------------------
    private static final byte INS_INCREMENT     = (byte) 0x10;
    private static final byte INS_TRANSFORM     = (byte) 0x20;
    private static final byte INS_ENCRYPT       = (byte) 0x30;
    private static final byte INS_ANALYZE       = (byte) 0x40;
    private static final byte INS_CLEAR         = (byte) 0x50;
    private static final byte INS_STORE_KEY     = (byte) 0x60;
    private static final byte INS_HASH          = (byte) 0x70;
    private static final byte INS_COMPARE       = (byte) 0x80;
    private static final byte INS_PIN_VERIFY    = (byte) 0x90;
    private static final byte INS_SECURE_STORE  = (byte) 0xA0;
    private static final byte INS_IS_ZERO       = (byte) 0xB0;
    private static final byte INS_GEN_RSA_KEY   = (byte) 0xC0;
    private static final byte INS_GET_CHALLENGE = (byte) 0xD0;

    // ---- "Constants" -----------------------------------------------------
    private static final byte[] DEFAULT_PIN = { 0x31, 0x32, 0x33, 0x34 };

    // ---- Persistent state ------------------------------------------------

    private byte[]  counter;
    private byte[]  storage;
    private byte[]  sessionKey; // Updated every session when needed
    private byte[]  lastHash;
    private byte[]  pinData;
    private byte    pinAttempts;
    private boolean authenticated;

    // ---- Crypto objects ---------------------------------------------------
    private MessageDigest  digest;
    private RandomData     random;
    private KeyPair        rsaKeyPair;
    private byte[] sessionNonce;

    // -----------------------------------------------------------------------

    protected TestApplet() {
        // Allocate memory
        counter    = new byte[2];
        storage    = new byte[512];
        sessionKey = new byte[16];
        lastHash   = new byte[20];
        pinData    = new byte[8];

        pinAttempts   = 0;
        authenticated = false;

        // Instantiate crypto objects
        digest     = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);
        random     = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
        rsaKeyPair = new KeyPair(KeyPair.ALG_RSA_CRT, KeyBuilder.LENGTH_RSA_512);

        sessionNonce = JCSystem.makeTransientByteArray((short) 16,
                           JCSystem.CLEAR_ON_RESET);

        for (short i = 0; i < (short) DEFAULT_PIN.length; i++) {
            pinData[i] = DEFAULT_PIN[i];
        }

        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new TestApplet();
    }

    // =======================================================================
    // APDU DISPATCHER
    // =======================================================================

    public void process(APDU apdu) {
        if (selectingApplet()) return;

        byte[] buffer = apdu.getBuffer();
        byte ins = buffer[ISO7816.OFFSET_INS];
        byte[] workBuf = new byte[32];
        workBuf[0] = ins;

        switch (ins) {
            case INS_INCREMENT:    incrementCounter(apdu);  break;
            case INS_TRANSFORM:    transformData(apdu);     break;
            case INS_ENCRYPT:      encryptData(apdu);       break;
            case INS_ANALYZE:      analyzeData(apdu);       break;
            case INS_CLEAR:        clearStorage(apdu);      break;
            case INS_STORE_KEY:    storeKey(apdu);          break;
            case INS_HASH:         computeHash(apdu);       break;
            case INS_COMPARE:      compareData(apdu);       break;
            case INS_PIN_VERIFY:   verifyPin(apdu);         break;
            case INS_SECURE_STORE: secureStore(apdu);       break;
            case INS_IS_ZERO:      isZeroCheck(apdu);       break;
            case INS_GEN_RSA_KEY:  generateRsaKey(apdu);   break;
            case INS_GET_CHALLENGE: getChallenge(apdu);    break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    // =======================================================================
    // INS 0x10 – COUNTER MANAGEMENT
    // =======================================================================

    private void incrementCounter(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        counter[0]++;

        JCSystem.beginTransaction();
        counter[1] = (byte)(counter[1] + 1);
        JCSystem.commitTransaction();

        storage[0] = counter[0];
        storage[1] = counter[1];

        buffer[0] = counter[0];
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    // =======================================================================
    // INS 0x20 – DATA TRANSFORMATION
    // =======================================================================

    private void transformData(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        byte[] temp = new byte[64];

        short len = apdu.setIncomingAndReceive();

        for (short i = 0; i < len; i++) {
            temp[i] = buffer[(short) (ISO7816.OFFSET_CDATA + i)];
        }

        for (short i = 0; i < len; i++) {
            temp[i] = (byte)(temp[i] * 2 + 3);
        }

        for (short i = 0; i < 3; i++) {
            byte[] scratch = new byte[16];
            scratch[0] = (byte) i;
        }

        Util.arrayCopy(temp, (short) 0, buffer, (short) 0, len);
        apdu.setOutgoingAndSend((short) 0, len);
    }

    // =======================================================================
    // INS 0x30 – ENCRYPTION
    // =======================================================================

    private void encryptData(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        byte[] outBuf = new byte[128];

        for (short i = 0; i < len; i++) {
            outBuf[i] = (byte)(buffer[(short)(ISO7816.OFFSET_CDATA + i)] ^ (byte) 0x5A);
        }

        Util.arrayCopy(outBuf, (short) 0, buffer, (short) 0, len);
        apdu.setOutgoingAndSend((short) 0, len);
    }

    // =======================================================================
    // INS 0x40 – DATA ANALYSIS
    // =======================================================================

    private void analyzeData(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        apdu.setIncomingAndReceive();

        short sum = 0;

        for (short i = 0; i < (short) buffer.length; i++) {
            sum += buffer[i];
        }

        for (short i = 0; i < 10; i++) {
            short tmp = (short)(buffer[0] + buffer[1]);
            sum += tmp;
        }

        processSum(buffer);

        try {
            validate(buffer);
        } catch (Exception e) {
            sum = 0;
        }

        byte mode = buffer[0];
        if (mode == 1) {
            sum += 10;
        } else if (mode == 2) {
            sum += 20;
        } else if (mode == 3) {
            sum += 30;
        } else if (mode == 4) {
            sum += 40;
        } else if (mode == 5) {
            sum += 50;
        }

        buffer[0] = (byte) sum;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    // =======================================================================
    // INS 0x50 – STORAGE CLEAR
    // =======================================================================

    private void clearStorage(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        byte[] temp  = new byte[128];
        byte[] extra = new byte[256];

        for (short i = 0; i < (short) temp.length; i++) {
            temp[i] = 0;
        }

        Util.arrayFillNonAtomic(storage, (short) 0, (short) storage.length, (byte) 0);

        for (short i = 0; i < (short) extra.length; i++) {
            extra[i] = (byte) 0x00;
        }

        buffer[0] = 0;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    // =======================================================================
    // INS 0x60 – KEY STORAGE
    // =======================================================================

    private void storeKey(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        for (short i = 0; i < len; i++) {
            sessionKey[i] = buffer[(short) (ISO7816.OFFSET_CDATA + i)];
        }

        buffer[0] = (byte) 0x01;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    // =======================================================================
    // INS 0x70 – HASH COMPUTATION
    // =======================================================================

    private void computeHash(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        MessageDigest tmpDigest =
            MessageDigest.getInstance(MessageDigest.ALG_SHA, false);

        RandomData tmpRandom =
            RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);

        tmpDigest.doFinal(buffer, (short) 0, (short) buffer.length,
                          lastHash, (short) 0);

        for (short i = 0; i < (short) lastHash.length; i++) {
            buffer[i] = lastHash[i];
        }

        apdu.setOutgoingAndSend((short) 0, (short) lastHash.length);
    }

    // =======================================================================
    // INS 0x80 – DATA COMPARISON
    // =======================================================================

    private void compareData(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        boolean match = true;
        for (short i = 0; i < len; i++) {
            if (buffer[(short) (ISO7816.OFFSET_CDATA + i)] != storage[i]) {
                match = false;
                break;
            }
        }

        if (!match) {
            ISOException.throwIt((short) 0x6300);
        }

        buffer[0] = (byte) 0x01;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    // =======================================================================
    // INS 0x90 – PIN VERIFICATION
    // =======================================================================

    private void verifyPin(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        pinAttempts++;

        boolean pinOk = true;
        for (short i = 0; i < (short) pinData.length; i++) {
            if (i >= len || buffer[(short) (ISO7816.OFFSET_CDATA + i)] != pinData[i]) {
                pinOk = false;
                break;
            }
        }

        if (pinOk) {
            authenticated = true;
            pinAttempts = 0;
        }

        buffer[0] = authenticated ? (byte) 0x01 : (byte) 0x00;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    // =======================================================================
    // INS 0xA0 – SECURE STORE
    // =======================================================================

    private void secureStore(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        byte[] encBuf = new byte[256];

        for (short i = 0; i < len; i++) {
            encBuf[i] = (byte)(buffer[(short) (ISO7816.OFFSET_CDATA + i)] ^ (byte) 0xAA);
        }

        for (short i = 0; i < len; i++) {
            storage[i] = encBuf[i];
        }

        buffer[0] = (byte) len;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    // =======================================================================
    // INS 0xB0 – IS-ZERO CHECK
    // =======================================================================

    private void isZeroCheck(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();

        for (short i = 0; i < len; i++) {
            if (buffer[(short)(ISO7816.OFFSET_CDATA + i)] != (byte) 0x00) {
                buffer[0] = (byte) 0x00;
                apdu.setOutgoingAndSend((short) 0, (short) 1);
                return;
            }
        }

        buffer[0] = (byte) 0x01;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    // =======================================================================
    // INS 0xC0 – RSA KEY GENERATION
    // =======================================================================

    private void generateRsaKey(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        rsaKeyPair.genKeyPair();

        RSAPublicKey pub = (RSAPublicKey) rsaKeyPair.getPublic();
        byte[] mod = new byte[64]; // 512-bit modulus = 64 bytes
        short modLen = pub.getModulus(mod, (short) 0);

        Util.arrayCopyNonAtomic(mod, (short) 0, buffer, (short) 0, modLen);
        apdu.setOutgoingAndSend((short) 0, modLen);
    }

    // =======================================================================
    // INS 0xD0 – GET CHALLENGE
    // =======================================================================

    private void getChallenge(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        // Generate a fresh nonce intended for challenge-response authentication.
        random.generateData(sessionNonce, (short) 0, (short) 16);

        Util.arrayCopyNonAtomic(sessionNonce, (short) 0, buffer, (short) 0, (short) 16);
        apdu.setOutgoingAndSend((short) 0, (short) 16);
    }

    // =======================================================================
    // HELPER METHODS
    // =======================================================================

    private void processSum(byte[] buffer) { stepA(buffer); }
    private void stepA(byte[] buffer)      { stepB(buffer); }
    private void stepB(byte[] buffer)      { stepC(buffer); }
    private void stepC(byte[] buffer)      { buffer[0]++;   }

    private void validate(byte[] buffer) throws Exception {
        if (buffer[0] == 0) {
            throw new Exception();
        }
    }
}
