import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./styles.css";
import "./redesign.css";
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <a className="skip-link" href="#main-content">
      Skip to content
    </a>
    <App />
  </StrictMode>,
);
