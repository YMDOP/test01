package com.love.interviewforge.ingest;

/** 解析出的一个文本块（段落级），带所属标题路径。 */
public record ParsedBlock(String headingPath, String text) {
}
