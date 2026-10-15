package com.kodraliu.localrock.shared.device

/**
 * Whether a robot answers when asked directly. The server's `online` flag cannot be trusted for
 * this: it only reflects the live connection while at least one robot is connected, and otherwise
 * falls back to a value captured at cloud import, so a lone robot that drops off keeps reading
 * as online.
 */
enum class Reachability { CHECKING, ONLINE, OFFLINE }
