package com.love.archive.common.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 这里原先还挂着 {@code @MapperScan} 与 {@code @ConditionalOnBean(SqlSessionFactory.class)}。
 * 那个条件在 MyBatis-Plus 建好 SqlSessionFactory 之前就被求值，实际上从不成立，
 * 于是整个配置被跳过：Mapper 一直是靠 starter 的自动扫描注册的（所有 Mapper 都带
 * {@code @Mapper} 且在 {@code com.love.archive} 下），而拦截器从未注册——
 * 分页因此静默失效，selectPage 返回 total=0 且不加 LIMIT。
 *
 * <p>现在只保留拦截器且不加条件：没有数据源时它也只是个普通 bean，不会有副作用；
 * Mapper 注册继续交给 starter。</p>
 */
@Configuration(proxyBeanMethods = false)
public class MybatisPlusConfiguration {

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
