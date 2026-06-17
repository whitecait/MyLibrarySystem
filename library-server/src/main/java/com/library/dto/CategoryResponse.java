package com.library.dto;

public class CategoryResponse {

    private Long id;
    private String name;
    private String addtime;

    public CategoryResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddtime() { return addtime; }
    public void setAddtime(String addtime) { this.addtime = addtime; }
}
