import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";

import App from "./App";
import ErrorBoundary from "./components/common/ErrorBoundary";
import { LanguageProvider } from "./i18n/LanguageContext";
import queryClient from "./services/queryClient";

import "./styles/reset.css";
import "./styles/variables.css";
import "./styles/global.css";
import "./styles/animations.css";
import "./styles/project.css";

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <LanguageProvider>
            <App />
          </LanguageProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  </React.StrictMode>
);
