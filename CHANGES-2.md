# Ikkinchi tuzatish to'plami

- Ghost (Show world): haqiqiy 3D (WorldRenderEvents.BEFORE_DEBUG_RENDER + RenderLayers.debugQuads). Xira 2D proyeksiya o'rniga
  chuqurlik bilan, faqat tashqi yuzlar, yoritish bilan. Bounding box 3D chiziqlar. "Remaining" ham 3D.
- Qurish: yo'nalishli bloklar (zinapoya, plita, log, pech, eshik...) uchun Block.getPlacementState bilan to'g'ri yaw/pitch/hit balandligi
  topiladi. Avval bunday bloklar noto'g'ri qo'yilib, "bajarilmagan" deb abadiy qayta uriniladi.
  Sandiq/eshik/tugma kabi interaktiv qo'shnilarga bosilmaydi (ochilib ketmasligi uchun).
- UI: 3D preview'da soya, pol to'ri, o'lchamlar, ko'rsatma; kutubxona scrollbar va tanlangan qator belgisi; Remaining/Opacity tugmalari;
  progress bar (reja = sariq, qurish = yashil) va foiz.
- Tunnel: Auto Tool sozlamasi (AutoTool modulini yoqadi/o'chiradi), survivalda bir blokni oxirigacha qazish (avval har tikda
  nishon almashib, progress nolga tushardi), to'g'ri yuz yo'nalishi, faqat oldi ochiq bo'lsa yurish.
- Developer kategoriyasi: sidebar tablar oynaga sig'adigan qilib moslashtirildi.
