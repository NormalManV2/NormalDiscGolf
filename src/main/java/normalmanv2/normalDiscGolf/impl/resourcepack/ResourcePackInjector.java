package normalmanv2.normalDiscGolf.impl.resourcepack;

import io.netty.channel.ChannelHandlerContext;
import net.mcbrawls.inject.api.InjectorContext;
import net.mcbrawls.inject.http.HttpByteBuf;
import net.mcbrawls.inject.http.HttpInjector;
import net.mcbrawls.inject.http.HttpRequest;
import org.jetbrains.annotations.NotNull;

public final class ResourcePackInjector extends HttpInjector {
    private final PackedIntegration packedIntegration;

    public ResourcePackInjector(PackedIntegration packedIntegration) {
        this.packedIntegration = packedIntegration;
    }

    @Override
    public boolean isRelevant(InjectorContext ctx, @NotNull HttpRequest request) {
        return request.getRequestURI().startsWith("/ndg/pack") || request.getRequestURI().equals("/ndg/check");
    }

    @NotNull
    @Override
    public HttpByteBuf intercept(ChannelHandlerContext ctx, HttpRequest request) {
        HttpByteBuf buf = HttpByteBuf.httpBuf(ctx);
        buf.writeStatusLine("1.1", 200, "OK");

        if (request.getRequestURI().equals("/ndg/check")) {
            buf.writeText("Yes");
            return buf;
        }

        byte[] packBytes = this.packedIntegration.getBytes();
        buf.writeHeader("Content-Type", "application/zip");
        buf.writeHeader("Content-Length", String.valueOf(packBytes.length));
        buf.writeBytes(packBytes);

        return buf;
    }
}
