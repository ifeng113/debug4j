import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Debug4j',
  description: '一款开源的 Java 远程调试工具 —— 让异网环境的调试像本地开发一样简单',
  ignoreDeadLinks: [
    /^http:\/\/localhost/,
  ],
  lang: 'zh-CN',
  lastUpdated: true,

  head: [
    ['link', { rel: 'icon', href: '/favicon.ico' }],
    ['meta', { name: 'theme-color', content: '#4fc08d' }],
    ['meta', { name: 'keywords', content: 'Java, 调试, 远程调试, 热更新, 代理穿透, ByteBuddy, Spring Boot' }],
  ],

  themeConfig: {
    logo: '/logo.png',

    nav: [
      { text: '首页', link: '/' },
      { text: '指南', link: '/guide/introduction' },
      { text: '功能', link: '/features/proxy-tunnel' },
      { text: '技术实现', link: '/technology/protocol' },
      { text: 'API', link: '/api/reference' },
    ],

    sidebar: {
      '/guide/': [
        {
          text: '指南',
          items: [
            { text: '产品介绍', link: '/guide/introduction' },
            { text: '快速开始', link: '/guide/getting-started' },
            { text: '架构原理', link: '/guide/architecture' },
            { text: '常见问题', link: '/guide/faq' },
          ],
        },
      ],
      '/features/': [
        {
          text: '功能特性',
          items: [
            { text: '代理穿透', link: '/features/proxy-tunnel' },
            { text: '源码管理', link: '/features/source-management' },
            { text: '进程管理', link: '/features/process-management' },
            { text: '日志管理', link: '/features/log-management' },
            { text: '组件增强', link: '/features/component-enhancement' },
          ],
        },
      ],
      '/technology/': [
        {
          text: '技术实现',
          items: [
            { text: '通信协议', link: '/technology/protocol' },
            { text: 'ByteBuddy 字节码增强', link: '/technology/bytebuddy' },
            { text: 'Core 核心引擎', link: '/technology/core-engine' },
            { text: 'Spring 集成', link: '/technology/spring-integration' },
          ],
        },
      ],
      '/api/': [
        {
          text: 'API 参考',
          items: [
            { text: '接口文档', link: '/api/reference' },
          ],
        },
      ],
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/ifeng113/debug4j' },
      { icon: 'gitee', link: 'https://gitee.com/ifeng113/debug4j' },
    ],

    footer: {
      message: '基于 Apache License 2.0 协议开源',
      copyright: 'Copyright © 2024-present k4ln',
    },

    editLink: {
      pattern: 'https://github.com/ifeng113/debug4j/edit/master/docs/:path',
      text: '在 GitHub 上编辑此页',
    },

    lastUpdatedText: '最后更新',
    docFooter: {
      prev: '上一页',
      next: '下一页',
    },
  },
})