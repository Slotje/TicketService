declare module 'aos' {
  interface AOSOptions {
    duration?: number;
    easing?: string;
    once?: boolean;
    offset?: number;
    delay?: number;
    disable?: boolean | string | (() => boolean);
  }
  function init(options?: AOSOptions): void;
  function refresh(hard?: boolean): void;
  function refreshHard(): void;
}
