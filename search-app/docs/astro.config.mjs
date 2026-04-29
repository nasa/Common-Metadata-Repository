import { defineConfig } from "astro/config";

import starlight from "@astrojs/starlight";

// https://astro.build/config
export default defineConfig({
  build: {
    format: 'file'
  },
  outDir: '/app/search-app/resources/public/site/docs/search/dist',
  integrations: [starlight({
    title: 'CMR API Documentation'
  })]
});