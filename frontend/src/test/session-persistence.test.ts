import { beforeEach, it, expect, vi } from "vitest";
import { session, restoreSession, SESSION_KEY } from "../features/auth/session";
import { auth } from "./fixtures";
beforeEach(() => session.clear());
it("restores a valid tab session synchronously", () => { const data=auth("EMPLOYEE");session.set(data);restoreSession();expect(session.get()?.token).toBe(data.token);expect(session.get()?.role).toBe("EMPLOYEE");expect(localStorage.length).toBe(0); });
it("hydrates a new module instance before protected route rendering", async () => { const data=auth();session.set(data);vi.resetModules();const fresh=await import("../features/auth/session");expect(fresh.session.get()?.token).toBe(data.token);fresh.session.clear(); });
it("rejects corrupted, expired and inconsistent stored sessions", () => {for(const stored of ["{",JSON.stringify(auth("ADMIN",Date.now()-1000)),JSON.stringify({...auth("EMPLOYEE"),role:"ADMIN"}),"null"]){sessionStorage.setItem(SESSION_KEY,stored);restoreSession();expect(session.get()).toBeNull();expect(sessionStorage.getItem(SESSION_KEY)).toBeNull();} });
it("logout deletes storage so refresh cannot restore it", () => {session.set(auth());session.clear();restoreSession();expect(session.get()).toBeNull();expect(sessionStorage.getItem(SESSION_KEY)).toBeNull();});
it("continues with in-memory login if storage is blocked", () => {const spy=vi.spyOn(Storage.prototype,"setItem").mockImplementation(()=>{throw new Error("blocked");});session.set(auth());expect(session.get()).not.toBeNull();spy.mockRestore();});
