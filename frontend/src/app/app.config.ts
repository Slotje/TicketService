import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { adminAuthInterceptor } from './interceptors/admin-auth.interceptor';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import { definePreset } from '@primeng/themes';
import Aura from '@primeng/themes/aura';

import { routes } from './app.routes';

const PremiumPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{amber.50}',
      100: '{amber.100}',
      200: '{amber.200}',
      300: '{amber.300}',
      400: '{amber.400}',
      500: '#d4a853',
      600: '#c49a4a',
      700: '#b8903e',
      800: '#a07a33',
      900: '#7d5f28',
      950: '#5a441c'
    },
    colorScheme: {
      light: {
        primary: {
          color: '#d4a853',
          contrastColor: '#0f172a',
          hoverColor: '#c49a4a',
          activeColor: '#b8903e'
        },
        highlight: {
          background: 'rgba(212, 168, 83, 0.12)',
          focusBackground: 'rgba(212, 168, 83, 0.2)',
          color: '#b8903e',
          focusColor: '#a07a33'
        },
        surface: {
          0: '#ffffff',
          50: '#faf9f7',
          100: '#f5f3f0',
          200: '#ece9e4',
          300: '#d6d2cc',
          400: '#b8b3ab',
          500: '#9a9489',
          600: '#7c756a',
          700: '#5e574e',
          800: '#403b33',
          900: '#1e293b',
          950: '#0f172a'
        }
      }
    }
  },
  primitive: {
    borderRadius: {
      none: '0',
      xs: '0.375rem',
      sm: '0.5rem',
      md: '0.625rem',
      lg: '0.75rem',
      xl: '1rem'
    }
  }
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([adminAuthInterceptor])),
    provideAnimationsAsync(),
    providePrimeNG({
      theme: {
        preset: PremiumPreset,
        options: {
          darkModeSelector: '.dark-mode'
        }
      }
    })
  ]
};
