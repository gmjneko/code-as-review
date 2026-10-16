package org.koaks.codereview.rag.service;

import java.util.List;

/**
 * 文本向量化抽象。
 *
 * <p>索引器只依赖这个接口，因此 Qwen/DashScope 可以在单元测试中替换成脚本化实现，
 * 也方便以后切换其他 embedding 服务。</p>
 */
public interface EmbeddingClient {
    /** 按输入顺序返回向量；实现必须保证返回数量与 texts 一致。 */
    List<float[]> embed(List<String> texts);
}
