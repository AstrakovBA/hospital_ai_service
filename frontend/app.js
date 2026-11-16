const API_URL = "http://localhost:8080/api/v1/patient/draft-answer";

const form = document.querySelector("#question-form");
const questionInput = document.querySelector("#patient-question");
const questionCounter = document.querySelector("#question-counter");
const submitButton = document.querySelector("#submit-button");
const resultCard = document.querySelector("#result-card");
const resultMeta = document.querySelector("#result-meta");
const answerText = document.querySelector("#answer-text");
const disclaimerText = document.querySelector("#disclaimer-text");
const errorMessage = document.querySelector("#error-message");
const copyButton = document.querySelector("#copy-button");

questionInput.addEventListener("input", () => {
  questionCounter.textContent = `${questionInput.value.length} / 2000`;
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!form.reportValidity()) return;

  const formData = new FormData(form);
  const request = Object.fromEntries(formData.entries());
  request.patientName = request.patientName.trim() || null;
  request.clinicName = request.clinicName.trim() || null;

  setLoading(true);
  hideError();
  resultCard.hidden = true;

  try {
    const response = await fetch(API_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
    const payload = await response.json().catch(() => null);

    if (!response.ok) {
      throw new Error(payload?.message || `Не удалось получить ответ (ошибка ${response.status}).`);
    }

    showResult(payload);
  } catch (error) {
    showError(error instanceof TypeError
      ? "Не удалось связаться с сервером. Проверьте, что backend запущен на http://localhost:8080."
      : error.message);
  } finally {
    setLoading(false);
  }
});

copyButton.addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(answerText.textContent);
    copyButton.textContent = "Скопировано";
    window.setTimeout(() => {
      copyButton.textContent = "Скопировать ответ";
    }, 1800);
  } catch {
    showError("Не удалось скопировать ответ. Выделите текст и скопируйте его вручную.");
  }
});

function showResult(result) {
  answerText.textContent = result.draftAnswer || "";
  disclaimerText.textContent = result.disclaimer || "";
  resultMeta.replaceChildren();
  addChip(result.provider || "AI");
  addChip(`${Number(result.latencyMs) || 0} мс`);
  if (result.fallbackUsed) addChip("Резервный ответ", true);
  resultCard.hidden = false;
  resultCard.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

function addChip(label, warning = false) {
  const chip = document.createElement("span");
  chip.className = warning ? "meta-chip warning" : "meta-chip";
  chip.textContent = label;
  resultMeta.append(chip);
}

function setLoading(loading) {
  submitButton.disabled = loading;
  submitButton.querySelector(".button-label").textContent = loading
    ? "Готовим ответ..."
    : "Составить черновик";
}

function showError(message) {
  errorMessage.textContent = message;
  errorMessage.hidden = false;
}

function hideError() {
  errorMessage.textContent = "";
  errorMessage.hidden = true;
}
