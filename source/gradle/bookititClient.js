import "dotenv/config";              // ← no .js
import axios from "axios";
import crypto from "crypto";

const PUBLIC  = process.env.BOOKITIT_PUBLIC_KEY;
const PRIVATE = process.env.BOOKITIT_PRIVATE_KEY;
const HOST    = process.env.BOOKITIT_HOST || "app.bookitit.com";
const ROOT    = (process.env.BOOKITIT_ROOT_PATH || "api/11").replace(/^\/|\/$/g, "");

if (!PUBLIC || !PRIVATE) {
  throw new Error("Missing BOOKITIT_PUBLIC_KEY / BOOKITIT_PRIVATE_KEY in .env");
}

function buildSignedPath(p_sUrl) {
  const clean = String(p_sUrl || "").replace(/^\/+/, "");
  return ROOT ? `${ROOT}/${clean}` : clean;
}
function hmacMd5Hex(pathForSig) {
  return crypto.createHmac("md5", PRIVATE).update(pathForSig, "utf8").digest("hex");
}
function makeBasicAuth(pathForSig) {
  const sig = hmacMd5Hex(pathForSig);
  const token = Buffer.from(`${PUBLIC}:${sig}`, "utf8").toString("base64");
  return `Basic ${token}`;
}

export async function startConnection({ urlPart, mode = "json", secure = true, method = "GET", postParams = null }) {
  const signedPath = buildSignedPath(urlPart);
  const scheme = secure ? "https" : "http";
  const fullUrl = `${scheme}://${HOST}/${signedPath}`;

  const headers = {
    Authorization: makeBasicAuth(urlPart.replace(/^\/+/, "")),
    Accept: `application/${mode}`,
    "Accept-Charset": "utf-8",
  };

  try {
    if (method.toUpperCase() === "POST") {
      headers["Content-Type"] = "application/x-www-form-urlencoded";
      const body = typeof postParams === "string" ? postParams : new URLSearchParams(postParams || {}).toString();
      const res = await axios.post(fullUrl, body, { headers, timeout: 15000 });
      return res.data;
    } else {
      const res = await axios.get(fullUrl, { headers, timeout: 15000 });
      return res.data;
    }
  } catch (err) {
    // Surface Bookitit’s response for easier debugging
    const msg = err?.response?.data ? JSON.stringify(err.response.data) : err.message;
    throw new Error(`[Bookitit ${method}] ${fullUrl} -> ${msg}`);
  }
}

/* ------------------- High-level helpers ------------------- */

export async function testConnection(echoString, mode = "json", secure = true) {
  const urlPart = `testconnection/${encodeURIComponent(String(echoString))}`;
  return startConnection({ urlPart, mode, secure, method: "GET" });
}

export async function addClient({ email = "", name = "", intl = "", cell = "", webaccess = false, passwordMd5 = "", document = "", address = "", phone = "" }, mode = "json", secure = true) {
  const urlPart = `addclient/${encodeURIComponent(PUBLIC)}`;   // ← use PUBLIC
  const form = {
    p_sWebaccess: webaccess ? "true" : "false",
    p_sPassword: passwordMd5,
    p_sEmail: email,
    p_sCellphone: cell,
    p_sInternationalCode: intl,
    p_sDocument: document,
    p_sName: name,
    p_sAddress: address,
    p_sPhone: phone,
  };
  return startConnection({ urlPart, mode, secure, method: "POST", postParams: form });
}

export async function findClientByEmail(email, mode = "json", secure = true) {
  const urlPart = `getclientbyvalidationfield/${encodeURIComponent(PUBLIC)}`;  // ← use PUBLIC
  const form = { p_sFieldType: "email", p_sFieldvalue: String(email || "").trim().toLowerCase() };
  return startConnection({ urlPart, mode, secure, method: "POST", postParams: form });
}


// … keep your startConnection as-is …

/** Helper: parse strings that sometimes come with single quotes or wrong content-type */
function coerceJSON(x) {
  if (typeof x !== "string") return x;
  try { return JSON.parse(x); } catch {}
  try { return JSON.parse(x.replace(/'/g, '"')); } catch {}
  return x; // give back the string if it really isn't JSON
}

/** Helper: extract an array of "HH:MM" strings from the many shapes Bookitit returns */
function extractHours(root) {
  if (!root) return [];
  const hoursRoot = root?.slots?.hours ?? root?.freeslots?.hours ?? root?.freeslots;
  if (!hoursRoot) return [];

  // ✅ your case: array of "HH:MM" strings
  if (Array.isArray(hoursRoot) && typeof hoursRoot[0] === "string") {
    return hoursRoot;
  }

  // common cases
  if (Array.isArray(hoursRoot?.hour)) return hoursRoot.hour;
  if (typeof hoursRoot?.hour === "string") return [hoursRoot.hour];

  // array of objects like [{hour:"10:00"}]
  if (Array.isArray(hoursRoot)) return hoursRoot.map(x => (typeof x === "string" ? x : x?.hour)).filter(Boolean);

  // nested objects
  if (typeof hoursRoot === "object") {
    const out = [];
    for (const v of Object.values(hoursRoot)) {
      if (!v) continue;
      if (typeof v === "string") out.push(v);
      else if (Array.isArray(v?.hour)) out.push(...v.hour);
      else if (typeof v?.hour === "string") out.push(v.hour);
    }
    return out;
  }

  if (typeof hoursRoot === "string") return [hoursRoot];
  return [];
}


/**
 * getFreeSlots — returns minute offsets (and also the HH:MM list)
 * includeFull: if true, include entries that start with '*' (full)
 */
export async function getFreeSlots({
  serviceId,
  agendaId,
  date,                 // YYYY-MM-DD
  includeFull = false,
  mode = "json",
  secure = true,
}) {
  if (!serviceId || !agendaId || !date) {
    throw new Error("getFreeSlots: missing serviceId, agendaId or date");
  }

  const PUB = process.env.BOOKITIT_PUBLIC_KEY;
  const urlPart =
    `getfreeslots/${encodeURIComponent(PUB)}/` +
    `${encodeURIComponent(serviceId)}/` +
    `${encodeURIComponent(agendaId)}/` +
    `${encodeURIComponent(date)}` +
    (includeFull ? "/true" : "");

  const raw = await startConnection({ urlPart, mode, secure, method: "GET" });
  const data = coerceJSON(raw);

  // If server signals error, surface it
  if (data?.slots?.status === false) {
    return { slots: [], hours: [], error: data?.slots?.message ?? "status:false" };
  }

  // Extract "HH:MM" list
  let hours = extractHours(data).map(String);

  // Remove leading "*" unless includeFull is true
  if (!includeFull) hours = hours.filter(h => !h.startsWith("*"));
  hours = hours.map(h => h.replace(/^\*/, ""));

  // Convert HH:MM -> minutes from midnight
  const toMin = s => {
    const [H, M] = s.split(":").map(n => parseInt(n, 10));
    return Number.isFinite(H) && Number.isFinite(M) ? H * 60 + M : null;
  };
  const slots = hours.map(toMin).filter(n => Number.isFinite(n)).sort((a, b) => a - b);

  if (process.env.DEBUG_BOOKITIT === "1") {
    console.log("[getFreeSlots]", { urlPart, hours, slots });
  }
  const statusFalse = String(data?.slots?.status).toLowerCase() === "false";
if (statusFalse) return { slots: [], hours: [], error: data?.slots?.message ?? "status:false" };


  return { slots, hours };
  
}



export async function addEvent({ agendaId, serviceId, date, startMinutes, endMinutes, notes = "", clientId, client, mode = "json", secure = true }) {
  if (!agendaId || !serviceId || !date || !Number.isFinite(startMinutes) || !Number.isFinite(endMinutes)) {
    throw new Error("addEvent: missing agendaId, serviceId, date, startMinutes or endMinutes");
  }
  const urlPart = `addevent/${encodeURIComponent(PUBLIC)}`;     // ← use PUBLIC

  const postParams = {
    p_sAgendaID: String(agendaId),
    p_sServiceID: String(serviceId),
    p_sDateFrom: date,
    p_sDateTo: date,
    p_iStartMinutes: String(startMinutes),
    p_iEndMinutes: String(endMinutes),
    p_sComments: notes || "",
  };

  if (clientId) {
    postParams.p_sIdClient = String(clientId);
  } else if (client && (client.name || client.email || client.cell)) {
    if (client.name) postParams.p_sClientName = client.name;
    if (client.email) postParams.p_sClientEmail = client.email;
    if (client.intl) postParams.p_sInternationalCode = client.intl;
    if (client.cell) postParams.p_sClientCellphone = client.cell;
  }
  return startConnection({ urlPart, mode, secure, method: "POST", postParams });
}

/*************************************************** */
export async function getAgendaConfiguration(agendaId) {
  const PUB = process.env.BOOKITIT_PUBLIC_KEY;
  const urlPart = `getagendaconfiguration/${encodeURIComponent(PUB)}/${encodeURIComponent(agendaId)}`;
  return startConnection({ urlPart, method: "GET" });
}

export async function getAgendasByService(serviceId) {
  const PUB = process.env.BOOKITIT_PUBLIC_KEY;
  const urlPart = serviceId
    ? `getagendas/${encodeURIComponent(PUB)}/${encodeURIComponent(serviceId)}`
    : `getagendas/${encodeURIComponent(PUB)}`;
  return startConnection({ urlPart, method: "GET" });
}

export async function addAgendaService({ agendaId, serviceId, duration, price }) {
  const PUB = process.env.BOOKITIT_PUBLIC_KEY;
  const urlPart = `addagendaservice/${encodeURIComponent(PUB)}`;
  const postParams = {
    p_sAgendaID: String(agendaId),
    p_sServiceID: String(serviceId),
  };
  if (duration) postParams.p_iDuration = String(duration);
  if (price)    postParams.p_dPrice = String(price);
  return startConnection({ urlPart, method: "POST", postParams });
}


/******************************************* */