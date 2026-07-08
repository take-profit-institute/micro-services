import {themes as prismThemes} from 'prism-react-renderer';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Candle Engineering',
  tagline: '투자 경험을 연결하는 Candle 기술 문서',
  favicon: 'img/favicon.svg',

  url: 'https://take-profit-institute.github.io',
  baseUrl: '/micro-services/',
  organizationName: 'take-profit-institute',
  projectName: 'micro-services',
  trailingSlash: false,

  onBrokenLinks: 'throw',
  markdown: {
    mermaid: true,
    hooks: {
      onBrokenMarkdownLinks: 'throw',
      onBrokenMarkdownImages: 'throw',
    },
  },
  themes: ['@docusaurus/theme-mermaid'],

  presets: [
    [
      'classic',
      {
        docs: {
          path: '../docs',
          routeBasePath: 'docs',
          sidebarPath: './sidebars.js',
          editUrl:
            'https://github.com/take-profit-institute/micro-services/edit/dev/docs/',
          showLastUpdateAuthor: true,
          showLastUpdateTime: true,
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      },
    ],
  ],

  themeConfig: {
    image: 'img/candle-social-card.svg',
    colorMode: {
      defaultMode: 'dark',
      disableSwitch: false,
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Candle',
      logo: {
        alt: 'Candle logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'engineeringSidebar',
          position: 'left',
          label: 'Engineering',
        },
        {
          href: 'https://github.com/take-profit-institute/micro-services',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Architecture',
          items: [
            {label: 'Batch', to: '/docs/batch/BATCH_ARCHITECTURE'},
            {label: 'Ranking', to: '/docs/ranking/RANKING_SERVICE'},
            {label: 'Trading', to: '/docs/trading/TRADING_SERVICE'},
          ],
        },
        {
          title: 'Project',
          items: [
            {
              label: 'GitHub Repository',
              href: 'https://github.com/take-profit-institute/micro-services',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Candle. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'bash', 'sql', 'protobuf'],
    },
  },
};

export default config;
