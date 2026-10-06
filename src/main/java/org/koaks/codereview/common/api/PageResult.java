package org.koaks.codereview.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(long page, long size, long total, List<T> records) {

    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> mapper) {
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(mapper).toList());
    }

}
