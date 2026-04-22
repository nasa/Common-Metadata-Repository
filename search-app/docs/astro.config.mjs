import { defineConfig } from "astro/config";

import starlight from "@astrojs/starlight";

// https://astro.build/config
export default defineConfig({
  outDir: '/app/dist',
  integrations: [starlight({
    title: 'CMR API Documentation'
  })]
});