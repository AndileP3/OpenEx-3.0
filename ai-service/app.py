"""
OpenEx 3.0 - Astromech (AI microservice), Week 3 of the plan.

STATUS: functional stub. It correctly implements:
  - a secure(ish) read-only wallet/trade reader that calls the backend API
  - a REST endpoint an agent (or a frontend chat widget) can call

It does NOT include a live LangChain + Ollama call, because that requires a
locally running Ollama model, which isn't available in the environment this
was built in. The hookup point is clearly marked below - swap in a real
LangChain agent call where indicated. Everything else here is real,
runnable code.

Run:
    pip install -r requirements.txt
    uvicorn app:app --reload --port 8000

Then: POST http://localhost:8000/ask  {"accountId": "...", "question": "what's my balance?"}
"""

import os
import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8080/api")
SYMBOL = os.environ.get("SYMBOL", "BTC-USD")

app = FastAPI(title="OpenEx Astromech AI Service")


class AskRequest(BaseModel):
    accountId: str
    question: str


async def get_balance(account_id: str) -> dict:
    async with httpx.AsyncClient() as client:
        resp = await client.get(f"{BACKEND_URL}/wallet/{account_id}")
        if resp.status_code != 200:
            raise HTTPException(status_code=404, detail="account not found")
        return resp.json()


async def get_recent_trades(symbol: str = SYMBOL) -> list:
    async with httpx.AsyncClient() as client:
        resp = await client.get(f"{BACKEND_URL}/trades/{symbol}")
        resp.raise_for_status()
        return resp.json()[:10]


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/ask")
async def ask(req: AskRequest):
    """
    Deliberately narrow scope per the plan's 'Pro Tips': the AI assistant
    only ever reads balance and recent trades for the given account - it
    cannot place orders, withdraw funds, or touch other accounts. This is
    the security boundary called out in Day 17/20.
    """
    balances = await get_balance(req.accountId)
    trades = await get_recent_trades()

    context = {
        "balances": balances,
        "recent_trades": trades,
    }

    # --------------------------------------------------------------------
    # LLM HOOKUP POINT: replace this block with a real LangChain agent call
    # against a local Ollama model, e.g.:
    #
    #   from langchain_community.llms import Ollama
    #   from langchain.agents import initialize_agent, Tool
    #
    #   llm = Ollama(model="llama3")
    #   tools = [
    #       Tool(name="get_balance", func=lambda _: balances, description="..."),
    #       Tool(name="get_recent_trades", func=lambda _: trades, description="..."),
    #   ]
    #   agent = initialize_agent(tools, llm, agent="zero-shot-react-description")
    #   answer = agent.run(req.question)
    #
    # This stub instead does simple rule-based responses so the endpoint is
    # runnable end-to-end without any external LLM dependency.
    # --------------------------------------------------------------------
    q = req.question.lower()
    if "balance" in q:
        answer = f"Your current balances are: {balances}"
    elif "trade" in q:
        if not trades:
            answer = "No trades have been recorded yet."
        else:
            last = trades[0]
            answer = f"Your most recent trade: {last['quantity']} @ {last['price']} on {SYMBOL}."
    else:
        answer = (
            "I can currently answer questions about your balance and recent "
            "trades. Try asking 'what's my balance?' or 'what was my last trade?'"
        )

    return {"answer": answer, "context": context}
