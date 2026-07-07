import React, {useEffect, useState} from 'react';
import {createPortal} from 'react-dom';
import OriginalMDXImg from '@theme-original/MDXComponents/Img';
import styles from './styles.module.css';

function isSvgSource(source) {
  return (
    typeof source === 'string' &&
    (source.startsWith('data:image/svg+xml') || source.includes('.svg'))
  );
}

export default function MDXImg(props) {
  const [open, setOpen] = useState(false);
  const svg = isSvgSource(props.src);

  useEffect(() => {
    if (!open) {
      return undefined;
    }

    const previousOverflow = document.body.style.overflow;
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    };

    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', closeOnEscape);

    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

  if (!svg) {
    return <OriginalMDXImg {...props} />;
  }

  return (
    <>
      <button
        type="button"
        className={`candle-svg-preview ${styles.preview} ${styles.previewButton}`}
        aria-label={`${props.alt ?? '아키텍처 도식'} 원본 크기로 확대`}
        onClick={() => setOpen(true)}>
        <OriginalMDXImg {...props} />
        <span className={styles.previewHint}>원본 벡터로 확대</span>
      </button>

      {open && createPortal(
        <div
          className={styles.overlay}
          role="dialog"
          aria-modal="true"
          aria-label={`${props.alt ?? '아키텍처 도식'} 확대 보기`}
          onClick={() => setOpen(false)}>
          <section className={styles.viewer} onClick={(event) => event.stopPropagation()}>
            <header className={styles.viewerHeader}>
              <div>
                <strong>VECTOR VIEW · 100%</strong>
                <span>가로로 이동해 전체 도식을 확인할 수 있습니다.</span>
              </div>
              <button type="button" onClick={() => setOpen(false)} aria-label="확대 보기 닫기">
                닫기 ×
              </button>
            </header>
            <div className={styles.canvas}>
              <img src={props.src} alt={props.alt ?? ''} className={styles.fullSizeImage} />
            </div>
          </section>
        </div>,
        document.body,
      )}
    </>
  );
}
