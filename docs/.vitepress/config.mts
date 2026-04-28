import {defineConfig} from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
    title: "Discord-MC-Chat",
    description: "DMCC",

    // prettier-ignore
    head: [
        ['link', { rel: 'icon', type: 'image/svg+xml', href: '/icon.svg' }]
    ],

    themeConfig: {
        logo: { src: '/icon.svg', width: 24, height: 24 },

        // https://vitepress.dev/reference/default-theme-config
        nav: [
            {text: 'Home', link: '/'},
        ],

        socialLinks: [
            {icon: 'github', link: 'https://github.com/Xujiayao/Discord-MC-Chat'}
        ]
    }
})
