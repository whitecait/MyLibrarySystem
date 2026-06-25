package com.library.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class BatchApproval {

    @NotEmpty(message = "审核ID列表不能为空")
    private List<Long> ids;

    @NotNull(message = "审核结果不能为空")
    private String approved;     // "是" / "否"

    private String reply;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
    public String getApproved() { return approved; }
    public void setApproved(String approved) { this.approved = approved; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
}
