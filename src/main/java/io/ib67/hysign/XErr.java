package io.ib67.hysign;

import java.util.Map;

public class XErr {
    public static final Map<Long, String> MESSAGE = Map.of(
            2148916233L, "The account doesn't have an Xbox account. Once they sign up for one (or login through minecraft.net to create one) then they can proceed with the login. This shouldn't happen with accounts that have purchased Minecraft with a Microsoft account, as they would've already gone through that Xbox signup process.",
            2148916235L, "The account is from a country where Xbox Live is not available/banned",
            2148916236L, "The account needs adult verification on Xbox page. (South Korea)",
            2148916237L, "The account needs adult verification on Xbox page. (South Korea)",
            2148916238L, "The account is a child (under 18) and cannot proceed unless the account is added to a Family by an adult. This only seems to occur when using a custom Microsoft Azure application. When using the Minecraft launchers client id, this doesn't trigger."
    );
}
