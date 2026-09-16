package com.library.bootstrap;

import com.library.identity.contract.IdentityModule;
import com.library.identity.contract.IdentityRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition Root（MLS-DOC-MD-0002 §2）：模块 Adapter 由组件扫描装配；
 * 本配置只承担聚合接口的装配与一致性检查。聚合实现标注
 * {@code autowireCandidate = false}：聚合接口不得注入应用用例
 * （MLS-DOC-MD-0004 §2），仅作为「模块 Adapter 完整实现模块接口」的装配证明。
 * B0 仅 IDENTITY 注册能力；后续批次的能力子接口与聚合门面在此登记。
 */
@Configuration
public class CompositionRoot {

    @Bean(autowireCandidate = false)
    IdentityModule identityModule(IdentityRegistration registration) {
        // B0：聚合接口当前仅含注册能力，能力子接口装配即完整聚合。
        return registration::register;
    }
}
