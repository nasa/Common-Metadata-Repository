import { defineConfig } from "astro/config";

import starlight from "@astrojs/starlight";

// https://astro.build/config
export default defineConfig({
  build: {
    format: 'file'
  },
  outDir: '/app/search-app/docs',
  integrations: [starlight({
    title: 'CMR API Documentation'
  })]
});