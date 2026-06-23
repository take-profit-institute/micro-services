package org.profit.candle.user.idempotency;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class RequestHasher {

    private static final String VERSION_PREFIX = "v1";
    private static final String COMMAND_METADATA_FIELD = "command_metadata";
    private static final String IDEMPOTENCY_KEY_FIELD = "idempotency_key";

    public String hash(String operation, String actorId, Message request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(VERSION_PREFIX.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(operation.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(actorId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(serializeDeterministic(clearIdempotencyKey(request)));
            return toLowerHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("request hash 계산 실패", e);
        }
    }

    private Message clearIdempotencyKey(Message request) {
        FieldDescriptor commandField =
                request.getDescriptorForType().findFieldByName(COMMAND_METADATA_FIELD);
        if (commandField == null || !request.hasField(commandField)) {
            return request;
        }
        Message commandMetadata = (Message) request.getField(commandField);
        FieldDescriptor keyField =
                commandMetadata.getDescriptorForType().findFieldByName(IDEMPOTENCY_KEY_FIELD);
        if (keyField == null) {
            return request;
        }
        Message clearedMetadata = commandMetadata.toBuilder().clearField(keyField).build();
        return request.toBuilder().setField(commandField, clearedMetadata).build();
    }

    private byte[] serializeDeterministic(Message message) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CodedOutputStream coded = CodedOutputStream.newInstance(out);
        coded.useDeterministicSerialization();
        message.writeTo(coded);
        coded.flush();
        return out.toByteArray();
    }

    private String toLowerHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
