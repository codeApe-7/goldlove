package com.love.archive.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.love.archive.guest.persistence.ProfileFieldDefinitionEntity;
import com.love.archive.guest.persistence.ProfileFieldDefinitionMapper;
import com.love.archive.testsupport.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 回归守卫：{@code MybatisPlusConfiguration} 曾带一个过早求值的 {@code @ConditionalOnBean}，
 * 整个配置被跳过，Mapper 靠 starter 兜底扫描仍能用，但分页拦截器从未注册——
 * selectPage 于是静默返回 total=0 且不加 LIMIT。这里直接盯住拦截器是否真的生效。
 */
class MybatisPaginationTest extends ApiIntegrationTest {

    @Autowired private ProfileFieldDefinitionMapper definitionMapper;

    @BeforeEach
    void cleanState() {
        resetDatabase();
    }

    @Test
    void selectPageAppliesLimitAndReportsTotal() {
        Page<ProfileFieldDefinitionEntity> page = definitionMapper.selectPage(
                Page.of(1, 3),
                Wrappers.<ProfileFieldDefinitionEntity>lambdaQuery()
                        .orderByAsc(ProfileFieldDefinitionEntity::getSortOrder));

        // 迁移种下 9 个核心字段定义（V8 去掉出生日期与两个抖音字段、加回年龄）。
        assertThat(page.getTotal()).isEqualTo(9L);
        assertThat(page.getRecords()).hasSize(3);
        assertThat(page.getRecords().getFirst().getFieldCode()).isEqualTo("gender");
    }

    @Test
    void secondPageSkipsTheFirstOnes() {
        Page<ProfileFieldDefinitionEntity> page = definitionMapper.selectPage(
                Page.of(2, 3),
                Wrappers.<ProfileFieldDefinitionEntity>lambdaQuery()
                        .orderByAsc(ProfileFieldDefinitionEntity::getSortOrder));

        assertThat(page.getRecords()).hasSize(3);
        // sort_order 10..90，第 2 页（每页 3 条）是 education/occupation/income_range。
        assertThat(page.getRecords().getFirst().getFieldCode()).isEqualTo("education");
    }
}
