import React, { Suspense } from "react";
import { ThemeProvider } from "styled-components";
import { IntlProvider } from "react-intl";
import { CacheProvider } from "rest-hooks";
import { QueryClient, QueryClientProvider } from "react-query";
import { IntercomProvider } from "react-use-intercom";

import en from "locales/en.json";
import cloudLocales from "packages/cloud/locales/en.json";
import GlobalStyle from "global-styles";
import { theme } from "packages/cloud/theme";

import { Routing } from "packages/cloud/routes";
import LoadingPage from "components/LoadingPage";
import ApiErrorBoundary from "components/ApiErrorBoundary";
import NotificationServiceProvider from "hooks/services/Notification";
import { AnalyticsInitializer } from "views/common/AnalyticsInitializer";
import { Feature, FeatureItem, FeatureService } from "hooks/services/Feature";
import { AuthenticationProvider } from "packages/cloud/services/auth/AuthService";
import { AppServicesProvider } from "./services/AppServicesProvider";

const messages = Object.assign({}, en, cloudLocales);

const INTERCOM_APP_ID =
  process.env.REACT_APP_INTERCOM_APP_ID ||
  window.REACT_APP_INTERCOM_APP_ID ||
  "";

const I18NProvider: React.FC = ({ children }) => (
  <IntlProvider locale="en" messages={messages}>
    {children}
  </IntlProvider>
);

const StyleProvider: React.FC = ({ children }) => (
  <ThemeProvider theme={theme}>
    <GlobalStyle />
    {children}
  </ThemeProvider>
);

const queryClient = new QueryClient();

const StoreProvider: React.FC = ({ children }) => (
  <CacheProvider>
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  </CacheProvider>
);

const Features: Feature[] = [
  {
    id: FeatureItem.AllowOAuthConnector,
  },
];

const App: React.FC = () => {
  return (
    <React.StrictMode>
      <StyleProvider>
        <I18NProvider>
          <StoreProvider>
            <Suspense fallback={<LoadingPage />}>
              <ApiErrorBoundary>
                <NotificationServiceProvider>
                  <FeatureService features={Features}>
                    <AppServicesProvider>
                      <AuthenticationProvider>
                        <IntercomProvider appId={INTERCOM_APP_ID}>
                          <AnalyticsInitializer>
                            <Routing />
                          </AnalyticsInitializer>
                        </IntercomProvider>
                      </AuthenticationProvider>
                    </AppServicesProvider>
                  </FeatureService>
                </NotificationServiceProvider>
              </ApiErrorBoundary>
            </Suspense>
          </StoreProvider>
        </I18NProvider>
      </StyleProvider>
    </React.StrictMode>
  );
};

export default React.memo(App);
