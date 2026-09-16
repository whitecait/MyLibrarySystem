package com.library.contract;

/**
 * 业务模块接口的唯一返回模式（MLS-DOC-MD-0004 §1）：
 * 业务拒绝以 {@link Rejected} 返回、不抛异常；基础设施失败抛
 * {@link InfrastructureFailure} 由应用用例回滚。
 */
public sealed interface ModuleResult<T> permits ModuleResult.Accepted, ModuleResult.Rejected {

    record Accepted<T>(T value) implements ModuleResult<T> {
        public static <T> Accepted<T> of(T value) {
            return new Accepted<>(value);
        }
    }

    /**
     * @param code           MD-0003 已登记稳定错误码
     * @param reasons        可展示的拒绝原因，保持顺序
     * @param currentVersion 冲突时目标聚合的当前版本，便于调用方重新确认（BR-0045）
     */
    record Rejected<T>(String code, java.util.List<String> reasons, Long currentVersion)
            implements ModuleResult<T> {

        public static <T> Rejected<T> of(String code, String... reasons) {
            return new Rejected<>(code, java.util.List.of(reasons), null);
        }

        public static <T> Rejected<T> of(String code, java.util.List<String> reasons) {
            return new Rejected<>(code, java.util.List.copyOf(reasons), null);
        }

        public static <T> Rejected<T> conflict(String code, long currentVersion, String... reasons) {
            return new Rejected<>(code, java.util.List.of(reasons), currentVersion);
        }
    }
}
