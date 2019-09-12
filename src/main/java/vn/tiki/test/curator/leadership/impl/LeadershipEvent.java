package vn.tiki.test.curator.leadership.impl;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LeadershipEvent {

    private String leaderId;

    private byte[] leaderData;

    private String localId;
}
