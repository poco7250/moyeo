package com.poco7250.moyeo.group.dto;

/**
 * 그룹 생성 응답. 만들자마자 공유할 수 있게 초대 링크를 같이 준다.
 */
public record GroupCreateResponse(GroupResponse group, InviteLinkResponse inviteLink) {
}
