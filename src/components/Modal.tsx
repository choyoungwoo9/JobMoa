import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
export function Modal({ onClose, labelId, className = '', children }: { onClose: () => void; labelId: string; className?: string; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current!
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog.showModal()
    const cancel = (event: Event) => { event.preventDefault(); onClose() }
    dialog.addEventListener('cancel', cancel)
    return () => { dialog.removeEventListener('cancel', cancel); dialog.close(); document.body.style.overflow = previous }
  }, [onClose])
  return <dialog ref={ref} aria-labelledby={labelId} className={className} onClick={(event) => { if (event.target === event.currentTarget) onClose() }}><div className="dialog-inner">{children}</div></dialog>
}
