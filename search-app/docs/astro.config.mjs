import { defineConfig } from "astro/config";

import starlight from "@astrojs/starlight";

// https://astro.build/config
export default defineConfig({
  build: {
    format: 'file'
  },
  base: '/site/docs/search/dist/',
  outDir: '/app/search-app/resources/public/site/docs/search/dist',
  integrations: [starlight({
    title: 'CMR API Documentation',
    pagefind: false,
    sidebar: [
      { label: 'General Request Information', link: '/1-general-request-information/' },
      {
        label: 'Searching For Concepts',
        autogenerate: { directory: 'searching-for-concepts' }
      },
      { label: 'Additional Topics', link: '/13-additional-topics/' }
    ]
  })]
});