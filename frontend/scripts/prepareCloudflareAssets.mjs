import { rmSync } from "node:fs";
import { fileURLToPath } from "node:url";

const redirectsFile = fileURLToPath(
    new URL("../dist/_redirects", import.meta.url)
);

rmSync(redirectsFile, { force: true });
console.log("Removed the Pages-only _redirects file from the Workers bundle.");
