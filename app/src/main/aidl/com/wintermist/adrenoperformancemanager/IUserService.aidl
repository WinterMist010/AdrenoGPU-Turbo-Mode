package com.wintermist.adrenoperformancemanager;

interface IUserService {
    void destroy();
    int getUid();
    String execCommand(in String command);
    String execCommandWithTimeout(in String command, in long timeoutMs);
}
