import { api } from "@/shared/api/client";
import type { ShareCreateRequest, ShareCreateResponse } from "./types";

export function createShare(body: ShareCreateRequest) {
  return api<ShareCreateResponse>("/api/v1/shares", { method: "POST", body });
}
