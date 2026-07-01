package org.profit.candle.trading.support.idempotency;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * 서버 측 canonical request hash (스펙 §3).
 *
 * SHA-256 입력:
 *   v1\n
 *   <full gRPC method name>\n
 *   <authenticated actor ID>\n
 *   <command_metadata.idempotency_key를 clear한 deterministic protobuf 직렬화>
 *
 * idempotency_key만 clear한다. request ID·trace ID 등 비결정적 metadata 값은
 * 애초에 message가 아니라 gRPC metadata로 오므로 hash에 포함되지 않는다.
 */
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

    /** command_metadata.idempotency_key를 비운 사본을 만든다. 다른 필드는 그대로 둔다. */
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

    /** 필드 순서가 고정된 결정론적 직렬화 (기본 toByteArray는 map 순서 등이 비결정적일 수 있다). */
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
