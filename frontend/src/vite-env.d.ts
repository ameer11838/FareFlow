/// <reference types="vite/client" />
/// <reference types="google.maps" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_GOOGLE_MAPS_API_KEY?: string
  /** Optional styled map id. Advanced markers need one; DEMO_MAP_ID is the default. */
  readonly VITE_GOOGLE_MAPS_MAP_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
