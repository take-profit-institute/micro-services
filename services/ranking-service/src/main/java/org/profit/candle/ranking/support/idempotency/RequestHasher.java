package org.profit.candle.ranking.support.idempotency;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Message;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class RequestHasher {

    /** request의 idempotency key를 제외한 명령 의미를 deterministic SHA-256으로 계산한다. */
    public String hash(String operation, String actorId, Message request) {
        try {
            Message clearedRequest = clearIdempotencyKey(request);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CodedOutputStream codedOutput = CodedOutputStream.newInstance(output);
            codedOutput.useDeterministicSerialization();
            clearedRequest.writeTo(codedOutput);
            codedOutput.flush();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("v1\n".getBytes(StandardCharsets.UTF_8));
            digest.update((operation + "\n").getBytes(StandardCharsets.UTF_8));
            digest.update((actorId + "\n").getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest.digest(output.toByteArray()));
        } catch (Exception exception) {
            throw new IllegalStateException("Ranking request hash calculation failed", exception);
        }
    }

    /** command_metadata.idempotency_key만 비운 요청 사본을 만든다. */
    private Message clearIdempotencyKey(Message request) {
        var commandField = request.getDescriptorForType().findFieldByName("command_metadata");
        if (commandField == null || !request.hasField(commandField)) {
            return request;
        }
        Message metadata = (Message) request.getField(commandField);
        var keyField = metadata.getDescriptorForType().findFieldByName("idempotency_key");
        Message clearedMetadata = metadata.toBuilder().clearField(keyField).build();
        return request.toBuilder().setField(commandField, clearedMetadata).build();
    }
}
